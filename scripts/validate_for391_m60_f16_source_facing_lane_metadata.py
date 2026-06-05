#!/usr/bin/env python3
"""Validate the FOR-391 M60 F16 source-facing local band lane metadata."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-391"
DECISION = "M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED"
CLASSIFICATION = "source-facing-local-band-lane-stable-diagnostic-only"
METADATA_NAME = "sourceFacingLocalBandLane"
METADATA_ID = "source-facing-local-band-lane"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-capturer-metadata-source-facing-local-band-lane-apres-for-390"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-390-isole-une-lane-locale-source-facing-m60-f16-8-utiles-0-regression-sans-activation"
)
FOR390_DECISION = "M60_F16_FULL_SCENE_REGRESSION_DISCRIMINATOR_RECORDED"
FOR390_CLASSIFICATION = "narrower-metadata-defendable"
FOR389_DECISION = "M60_F16_SOURCE_COVERAGE_FULL_SCENE_CANDIDATE_EVALUATED"
FOR389_CLASSIFICATION = "full-scene-regresses"
SOURCE_CANDIDATE_ID = "source-color-and-oriented-coverage-lane"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-metadata-for391"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-source-facing-lane-metadata-for391.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-05-for-391-m60-f16-source-facing-lane-metadata.md"
FOR390_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-full-scene-regression-discriminator-for390/"
    "m60-f16-full-scene-regression-discriminator-for390.json"
)
FOR389_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-coverage-full-scene-candidate-for389/"
    "m60-f16-source-coverage-full-scene-candidate-for389.json"
)
CAPTURE_PRODUCER = (
    PROJECT_ROOT / "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt"
)

FORBIDDEN_DERIVATION_FIELDS = {
    "referenceRgba",
    "currentRgba",
    "probeRgba",
    "currentResidual",
    "probeResidual",
    "deltaVsCurrent",
    "gainVsCurrent",
    "currentErrorByChannel",
    "probeErrorByChannel",
    "probeMinusCurrentErrorByChannel",
    "probeMinusCurrentRgba",
    "membershipCategory",
    "membershipReason",
    "for389EvaluationResult",
    "for390EvaluationResult",
    "bestDiscriminator",
    "truth",
}
VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    (
        "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
        ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest"
    ),
    "rtk python3 scripts/validate_for391_m60_f16_source_facing_lane_metadata.py",
    "rtk python3 scripts/validate_for390_m60_f16_full_scene_regression_discriminator.py",
    "rtk python3 scripts/validate_for389_m60_f16_source_coverage_full_scene_candidate.py",
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
]

Pixel = dict[str, Any]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-391 validation failed: {message}")


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


def source_facing_local_band_lane(pixel: Pixel) -> bool:
    stroke_band = pixel["strokeBand"]
    band_local_x = int(pixel["bandLocalX"])
    return (
        stroke_band == "round-round" and band_local_x >= 39
    ) or (
        stroke_band == "butt-bevel" and band_local_x <= 17
    )


def source_facing_reason(pixel: Pixel) -> str:
    if not source_facing_local_band_lane(pixel):
        return "outside-source-facing-local-band-lane"
    if pixel["strokeBand"] == "round-round":
        return "round-round-right-terminal-bandLocalX-ge-39"
    return "butt-bevel-source-side-bandLocalX-le-17"


def selected_kind(pixel: Pixel) -> str:
    result = pixel.get("for389EvaluationResult")
    if result == "source-local-useful":
        return "improved"
    if result == "regressed-if-corrected":
        return "regressed"
    return "unchanged"


def residual_stats(pixels: list[Pixel]) -> dict[str, int]:
    before = sum(int(pixel["currentResidual"]) for pixel in pixels)
    after = sum(int(pixel["probeResidual"]) for pixel in pixels)
    return {
        "count": len(pixels),
        "beforeResidual": before,
        "afterResidual": after,
        "deltaVsCurrent": after - before,
        "gainVsCurrent": before - after,
    }


def validate_sources() -> tuple[dict[str, Any], dict[str, Any]]:
    for390 = load_json(FOR390_ARTIFACT)
    require(for390.get("decision") == FOR390_DECISION, "FOR-390 decision changed")
    require(for390.get("classification") == FOR390_CLASSIFICATION, "FOR-390 classification changed")
    require(for390.get("auditDoesNotApplyRendererChange") is True, "FOR-390 must remain diagnostic-only")
    require(for390.get("correctionAppliedByDefault") is False, "FOR-390 correction must remain disabled")
    best = for390.get("bestDiscriminator")
    require(isinstance(best, dict), "FOR-390 best discriminator missing")
    require(best.get("id") == METADATA_ID, "FOR-390 best discriminator id changed")
    require(best.get("selectedPixels") == 8, "FOR-390 best selected count changed")
    require(best.get("improvedPixelsRecovered") == 8, "FOR-390 best improved count changed")
    require(best.get("regressionsIncluded") == 0, "FOR-390 best regressions count changed")

    for389 = load_json(FOR389_ARTIFACT)
    require(for389.get("decision") == FOR389_DECISION, "FOR-389 decision changed")
    require(for389.get("classification") == FOR389_CLASSIFICATION, "FOR-389 classification changed")
    require(for389.get("auditDoesNotApplyRendererChange") is True, "FOR-389 must remain diagnostic-only")
    require(for389.get("correctionAppliedByDefault") is False, "FOR-389 correction must remain disabled")
    candidate = for389.get("candidate")
    require(isinstance(candidate, dict), "FOR-389 candidate missing")
    require(candidate.get("id") == SOURCE_CANDIDATE_ID, "FOR-389 candidate id changed")
    require(candidate.get("selectedPixels") == 16, "FOR-389 selected count changed")
    require(candidate.get("improvedPixels") == 8, "FOR-389 improved count changed")
    require(candidate.get("regressedPixels") == 8, "FOR-389 regressed count changed")

    source = CAPTURE_PRODUCER.read_text(encoding="utf-8")
    for needle in (
        "bandLocalX = membership.pixel.x - band.xStart",
        "strokeBand = band.id",
        '"runtimeHookInstalled": false',
        '"correctionAppliedByDefault": false',
        '"correctionPredicateEnabled": false',
    ):
        require(needle in source, f"capture source missing metadata/non-activation proof: {needle}")
    return for390, for389


def metadata_pixel(pixel: Pixel) -> dict[str, Any]:
    lane = source_facing_local_band_lane(pixel)
    return {
        "x": pixel["x"],
        "y": pixel["y"],
        "sourceFacingLocalBandLane": lane,
        "sourceFacingLocalBandLaneReason": source_facing_reason(pixel),
        "derivationInputs": {
            "strokeBand": pixel["strokeBand"],
            "bandLocalX": pixel["bandLocalX"],
        },
        "preCorrectionRendererMetadata": {
            "strokeBand": pixel["strokeBand"],
            "cap": pixel["cap"],
            "join": pixel["join"],
            "bandLocalX": pixel["bandLocalX"],
            "bandWidth": pixel["bandWidth"],
            "bandEdgeDistance": pixel["bandEdgeDistance"],
        },
        "evaluationOnly": {
            "for389EvaluationResult": pixel["for389EvaluationResult"],
            "kind": selected_kind(pixel),
            "currentResidual": pixel["currentResidual"],
            "probeResidual": pixel["probeResidual"],
            "deltaVsCurrent": pixel["deltaVsCurrent"],
        },
    }


def build_artifact() -> dict[str, Any]:
    for390, for389 = validate_sources()
    samples = for389.get("selectedPixelSamples")
    require(isinstance(samples, list), "FOR-389 selected samples missing")
    require(len(samples) == 16, "FOR-389 selected sample count changed")

    metadata_pixels = [metadata_pixel(pixel) for pixel in samples]
    selected = [
        pixel
        for pixel in samples
        if source_facing_local_band_lane(pixel)
    ]
    improved = [pixel for pixel in selected if selected_kind(pixel) == "improved"]
    regressions = [pixel for pixel in selected if selected_kind(pixel) == "regressed"]
    unchanged = [pixel for pixel in selected if selected_kind(pixel) == "unchanged"]
    selected_residual = residual_stats(selected)
    full_scene_before = for389["fullSceneImpactIfAppliedToSelectedPixelsOnly"][
        "baseUncorrectedFullSceneResidual"
    ]
    estimated_after = (
        full_scene_before
        - selected_residual["beforeResidual"]
        + selected_residual["afterResidual"]
    )

    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-source-facing-lane-metadata-for391",
        "sourceSceneId": for390["sceneId"],
        "sourceArtifact": rel(FOR390_ARTIFACT),
        "secondarySourceArtifact": rel(FOR389_ARTIFACT),
        "adapter": for389["adapter"],
        "producer": "scripts/validate_for391_m60_f16_source_facing_lane_metadata.py",
        "producerInput": "FOR-389 selected full-scene samples plus FOR-390 discriminator evidence",
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "requiredFor390Decision": FOR390_DECISION,
        "requiredFor390Classification": FOR390_CLASSIFICATION,
        "requiredFor389Decision": FOR389_DECISION,
        "requiredFor389Classification": FOR389_CLASSIFICATION,
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": [
            "source-facing-local-band-lane-stable-diagnostic-only",
            "source-facing-local-band-lane-counter-drift",
            "source-facing-local-band-lane-oracle-contaminated",
        ],
        "auditDoesNotProduceCorrection": True,
        "auditDoesNotApplyRendererChange": True,
        "correctionAppliedByDefault": False,
        "correctionPredicateEnabled": False,
        "runtimeHookInstalled": False,
        "metadata": {
            "name": METADATA_NAME,
            "id": METADATA_ID,
            "description": "Diagnostic source-facing local band lane derived from renderer-owned stroke band metadata before correction.",
            "status": "diagnostic-pre-correction-metadata",
            "derivedPreCorrection": True,
            "rendererOwnedInputMetadata": True,
            "selectionPrimaryField": METADATA_NAME,
            "derivationFormula": (
                "(strokeBand == round-round && bandLocalX >= 39) || "
                "(strokeBand == butt-bevel && bandLocalX <= 17)"
            ),
            "derivationRules": [
                {
                    "strokeBand": "round-round",
                    "bandLocalX": ">= 39",
                    "sourceFacingLocalBandLane": True,
                },
                {
                    "strokeBand": "butt-bevel",
                    "bandLocalX": "<= 17",
                    "sourceFacingLocalBandLane": True,
                },
            ],
            "derivationFields": ["strokeBand", "bandLocalX"],
            "forbiddenDerivationFields": sorted(FORBIDDEN_DERIVATION_FIELDS),
            "usesSkiaReferenceForDerivation": False,
            "usesProbeOutcomeForDerivation": False,
            "usesProbeResidualForDerivation": False,
            "usesCurrentResidualForDerivation": False,
            "usesDeltaVsCurrentForDerivation": False,
            "usesFor389TruthForDerivation": False,
            "usesFor390TruthForDerivation": False,
        },
        "selection": {
            "scope": "FOR-389-selected-full-scene-pixels",
            "refinesCandidateId": SOURCE_CANDIDATE_ID,
            "selectionMethod": f"{METADATA_NAME} == true",
            "selectedPixels": len(selected),
            "improvedPixelsRecovered": len(improved),
            "improvedTruth": 8,
            "regressionsIncluded": len(regressions),
            "unchangedIncluded": len(unchanged),
            "precision": 0.0 if not selected else round(len(improved) / len(selected), 4),
            "recall": round(len(improved) / 8, 4),
            "selectedResidualIfApplied": selected_residual,
            "estimatedFullSceneResidualIfAppliedToThisSubset": {
                "baseUncorrectedFullSceneResidual": full_scene_before,
                "simulatedAfterResidual": estimated_after,
                "deltaVsCurrent": estimated_after - full_scene_before,
                "gainVsCurrent": full_scene_before - estimated_after,
            },
            "selectedPixelCoordinates": [
                {"x": pixel["x"], "y": pixel["y"]}
                for pixel in selected
            ],
        },
        "metadataPixels": metadata_pixels,
        "for390Reproduction": {
            "sourceBestDiscriminatorId": for390["bestDiscriminator"]["id"],
            "selectedPixelsMatchFor390": len(selected) == for390["bestDiscriminator"]["selectedPixels"],
            "improvedPixelsMatchFor390": len(improved) == for390["bestDiscriminator"]["improvedPixelsRecovered"],
            "regressionsMatchFor390": len(regressions) == for390["bestDiscriminator"]["regressionsIncluded"],
            "estimatedResidualMatchesFor390": (
                estimated_after
                == for390["bestDiscriminator"]["estimatedFullSceneResidualIfAppliedToThisSubset"][
                    "simulatedAfterResidual"
                ]
            ),
        },
        "antiOraclePolicy": {
            "metadataMayUseSkiaReference": False,
            "metadataMayUseProbeOutcome": False,
            "metadataMayUseProbeResidual": False,
            "metadataMayUseCurrentResidual": False,
            "metadataMayUseDeltaVsCurrent": False,
            "metadataMayUseFor389Truth": False,
            "metadataMayUseFor390Truth": False,
            "evaluationMayUseTruthAndResiduals": True,
        },
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
            "runtimeHookInstalled": False,
            "probeEnabledByDefault": False,
        },
        "classificationReason": (
            "The explicit sourceFacingLocalBandLane metadata is derived only from pre-correction "
            "strokeBand and bandLocalX metadata, then reproduces the FOR-390 best discriminator "
            "counts without using reference, probe, residual, delta, or FOR-389/FOR-390 truth as "
            "selection inputs."
        ),
        "nextMove": (
            "Keep FOR-391 diagnostic-only. A separate future ticket must evaluate any runtime "
            "predicate behind an explicit opt-in guard before activation."
        ),
        "validationCommands": VALIDATION_COMMANDS,
    }
    return artifact


def write_artifacts(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    write_report(data)


def write_report(data: dict[str, Any]) -> None:
    selection = data["selection"]
    selected_rows = "\n".join(
        "| ({x},{y}) | `{lane}` | `{band}` | {local_x} | `{kind}` | {before} -> {after} |".format(
            x=pixel["x"],
            y=pixel["y"],
            lane=pixel["sourceFacingLocalBandLane"],
            band=pixel["derivationInputs"]["strokeBand"],
            local_x=pixel["derivationInputs"]["bandLocalX"],
            kind=pixel["evaluationOnly"]["kind"],
            before=pixel["evaluationOnly"]["currentResidual"],
            after=pixel["evaluationOnly"]["probeResidual"],
        )
        for pixel in data["metadataPixels"]
    )
    report = f"""# FOR-391 M60 F16 source-facing local band lane metadata

Decision: `{data["decision"]}`

Classification: `{data["classification"]}`

Artifact: `{rel(ARTIFACT)}`

Sources:

- `{data["sourceArtifact"]}`
- `{data["secondarySourceArtifact"]}`

FOR-391 reste diagnostic-only. Il nomme et exporte la metadata
`{METADATA_NAME}`, sans correction, sans hook runtime, sans changement WGSL,
fallback, scoring, seuil ou promotion.

## Metadata

`{METADATA_NAME}` est derivee avant correction depuis les metadata renderer
`strokeBand` et `bandLocalX` :

- `round-round && bandLocalX >= 39` ;
- `butt-bevel && bandLocalX <= 17`.

Les champs interdits pour la derivation sont la reference Skia, le probe, les
residuels, `deltaVsCurrent`, et les verites FOR-389/FOR-390. Ils restent
seulement disponibles dans `evaluationOnly` pour verifier les compteurs.

## Resultat

- pixels selectionnes : {selection["selectedPixels"]} ;
- ameliores recuperes : {selection["improvedPixelsRecovered"]}/8 ;
- regressions incluses : {selection["regressionsIncluded"]}/8 ;
- precision : {selection["precision"]:.4f} ;
- rappel : {selection["recall"]:.4f} ;
- residuel full-scene estime : {selection["estimatedFullSceneResidualIfAppliedToThisSubset"]["baseUncorrectedFullSceneResidual"]} -> {selection["estimatedFullSceneResidualIfAppliedToThisSubset"]["simulatedAfterResidual"]}.

Ces compteurs reproduisent FOR-390 pour `{METADATA_ID}` tout en rendant le
signal explicite comme metadata diagnostique stable.

## Pixels exportes

| Coord | sourceFacingLocalBandLane | Stroke band | bandLocalX | Evaluation | Residuel |
|---|---:|---|---:|---|---:|
{selected_rows}

## Non-activation

FOR-391 ne modifie pas le renderer, n'installe aucun hook runtime et n'active
aucune correction. Toute evaluation runtime reste hors portee de ce ticket.
"""
    REPORT.write_text(report, encoding="utf-8")


def validate_artifact(data: dict[str, Any]) -> None:
    require(data.get("schemaVersion") == 1, "schema version changed")
    require(data.get("linear") == LINEAR_ID, "linear id changed")
    require(data.get("decision") == DECISION, "decision changed")
    require(data.get("classification") == CLASSIFICATION, "classification changed")
    require(data.get("sourceMemory") == SOURCE_MEMORY, "source memory changed")
    require(data.get("sourceFinding") == SOURCE_FINDING, "source finding changed")
    require(data.get("requiredFor390Decision") == FOR390_DECISION, "FOR-390 decision requirement changed")
    require(data.get("requiredFor390Classification") == FOR390_CLASSIFICATION, "FOR-390 class requirement changed")
    require(data.get("requiredFor389Decision") == FOR389_DECISION, "FOR-389 decision requirement changed")
    require(data.get("requiredFor389Classification") == FOR389_CLASSIFICATION, "FOR-389 class requirement changed")
    for key in ("auditDoesNotProduceCorrection", "auditDoesNotApplyRendererChange"):
        require(data.get(key) is True, f"{key} must be true")
    for key in ("correctionAppliedByDefault", "correctionPredicateEnabled", "runtimeHookInstalled"):
        require(data.get(key) is False, f"{key} must be false")

    metadata = data.get("metadata")
    require(isinstance(metadata, dict), "metadata definition missing")
    require(metadata.get("name") == METADATA_NAME, "metadata name changed")
    require(metadata.get("id") == METADATA_ID, "metadata id changed")
    require(metadata.get("derivedPreCorrection") is True, "metadata must be pre-correction")
    require(metadata.get("rendererOwnedInputMetadata") is True, "metadata input ownership changed")
    require(metadata.get("derivationFields") == ["strokeBand", "bandLocalX"], "derivation fields changed")
    require(not FORBIDDEN_DERIVATION_FIELDS.intersection(metadata.get("derivationFields", [])), "forbidden derivation field used")
    formula = metadata.get("derivationFormula")
    require(isinstance(formula, str), "formula missing")
    require("strokeBand == round-round && bandLocalX >= 39" in formula, "round-round formula missing")
    require("strokeBand == butt-bevel && bandLocalX <= 17" in formula, "butt-bevel formula missing")
    for key in (
        "usesSkiaReferenceForDerivation",
        "usesProbeOutcomeForDerivation",
        "usesProbeResidualForDerivation",
        "usesCurrentResidualForDerivation",
        "usesDeltaVsCurrentForDerivation",
        "usesFor389TruthForDerivation",
        "usesFor390TruthForDerivation",
    ):
        require(metadata.get(key) is False, f"{key} must be false")

    selection = data.get("selection")
    require(isinstance(selection, dict), "selection missing")
    require(selection.get("scope") == "FOR-389-selected-full-scene-pixels", "selection scope changed")
    require(selection.get("selectionMethod") == f"{METADATA_NAME} == true", "selection method changed")
    require(selection.get("selectedPixels") == 8, "selected count changed")
    require(selection.get("improvedPixelsRecovered") == 8, "improved count changed")
    require(selection.get("improvedTruth") == 8, "improved truth changed")
    require(selection.get("regressionsIncluded") == 0, "regressions count changed")
    require(selection.get("unchangedIncluded") == 0, "unchanged count changed")
    require(selection.get("precision") == 1.0, "precision changed")
    require(selection.get("recall") == 1.0, "recall changed")
    residual = selection.get("estimatedFullSceneResidualIfAppliedToThisSubset")
    require(isinstance(residual, dict), "residual estimate missing")
    require(residual.get("baseUncorrectedFullSceneResidual") == 2014, "base residual changed")
    require(residual.get("simulatedAfterResidual") == 1949, "estimated residual changed")
    selected_residual = selection.get("selectedResidualIfApplied")
    require(isinstance(selected_residual, dict), "selected residual missing")
    require(selected_residual.get("beforeResidual") == 734, "selected before residual changed")
    require(selected_residual.get("afterResidual") == 669, "selected after residual changed")

    pixels = data.get("metadataPixels")
    require(isinstance(pixels, list), "metadata pixels missing")
    require(len(pixels) == 16, "metadata pixel count changed")
    selected_pixels = [pixel for pixel in pixels if pixel.get(METADATA_NAME) is True]
    rejected_pixels = [pixel for pixel in pixels if pixel.get(METADATA_NAME) is False]
    require(len(selected_pixels) == 8, "metadata true count changed")
    require(len(rejected_pixels) == 8, "metadata false count changed")
    require(
        sum(1 for pixel in selected_pixels if pixel["evaluationOnly"]["kind"] == "improved") == 8,
        "metadata true improved count changed",
    )
    require(
        sum(1 for pixel in selected_pixels if pixel["evaluationOnly"]["kind"] == "regressed") == 0,
        "metadata true regression count changed",
    )
    for pixel in pixels:
        derivation = pixel.get("derivationInputs")
        require(isinstance(derivation, dict), "pixel derivation inputs missing")
        require(set(derivation) == {"strokeBand", "bandLocalX"}, "pixel derivation inputs changed")
        require(not FORBIDDEN_DERIVATION_FIELDS.intersection(derivation), "pixel derivation uses forbidden field")
        expected = (
            derivation["strokeBand"] == "round-round" and int(derivation["bandLocalX"]) >= 39
        ) or (
            derivation["strokeBand"] == "butt-bevel" and int(derivation["bandLocalX"]) <= 17
        )
        require(pixel.get(METADATA_NAME) is expected, "pixel metadata derivation changed")

    reproduction = data.get("for390Reproduction")
    require(isinstance(reproduction, dict), "FOR-390 reproduction missing")
    for key in (
        "selectedPixelsMatchFor390",
        "improvedPixelsMatchFor390",
        "regressionsMatchFor390",
        "estimatedResidualMatchesFor390",
    ):
        require(reproduction.get(key) is True, f"{key} must be true")

    policy = data.get("antiOraclePolicy")
    require(isinstance(policy, dict), "anti-oracle policy missing")
    for key, value in policy.items():
        if key == "evaluationMayUseTruthAndResiduals":
            require(value is True, "evaluation truth flag changed")
        else:
            require(value is False, f"{key} must be false")

    non_goals = data.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal {key} must remain false")


def validate_report() -> None:
    require(REPORT.is_file(), f"missing report: {rel(REPORT)}")
    text = REPORT.read_text(encoding="utf-8")
    for needle in (
        DECISION,
        CLASSIFICATION,
        METADATA_NAME,
        "round-round && bandLocalX >= 39",
        "butt-bevel && bandLocalX <= 17",
        "8/8",
        "0/8",
        "2014 -> 1949",
        "sans hook runtime",
        "aucune correction",
    ):
        require(needle in text, f"report missing {needle}")


def main() -> None:
    data = build_artifact()
    write_artifacts(data)
    persisted = load_json(ARTIFACT)
    require(persisted == data, "persisted artifact differs from generated artifact")
    validate_artifact(persisted)
    validate_report()
    selection = persisted["selection"]
    print(
        "FOR-391 validation passed: "
        f"{persisted['classification']} / selected={selection['selectedPixels']} "
        f"improved={selection['improvedPixelsRecovered']} regressions={selection['regressionsIncluded']} "
        f"estimatedResidual={selection['estimatedFullSceneResidualIfAppliedToThisSubset']['simulatedAfterResidual']}"
    )


if __name__ == "__main__":
    main()
